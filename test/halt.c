/* halt.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that shuts down the OS.
 *
 * 	NOTE: for some reason, user programs with global data structures 
 *	sometimes haven't worked in the Nachos environment.  So be careful
 *	out there!  One option is to allocate data structures as 
 * 	automatics within a procedure, but if you do this, you have to
 *	be careful to allocate a big enough stack to hold the automatics!
 */

#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int
main()
{
    // halt();
    char *argv[3];
	argv[0] = "world";
	argv[1] = "input.txt";
	argv[2] = "69";
	

    int status = -100;	

    // int p2 = exec("matmult.coff", 0, argv);
    // int p3 = exec("write1.coff", 0, argv);
    // join(p3, &status);
    // join(p2, &status);

	int p4 = exec("cat.coff", 2, argv);
	status = join(p4, &status);


    printf("end of halt, %d\n", status);
    /* not reached */
}
